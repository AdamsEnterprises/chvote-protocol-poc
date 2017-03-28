package ch.ge.ve.protopoc.service.algorithm;

import ch.ge.ve.protopoc.service.exception.IncompatibleParametersException;
import ch.ge.ve.protopoc.service.exception.InvalidObliviousTransferResponseException;
import ch.ge.ve.protopoc.service.exception.NotEnoughPrimesInGroupException;
import ch.ge.ve.protopoc.service.model.*;
import ch.ge.ve.protopoc.service.model.polynomial.Point;
import ch.ge.ve.protopoc.service.support.ByteArrayUtils;
import ch.ge.ve.protopoc.service.support.Conversion;
import ch.ge.ve.protopoc.service.support.Hash;
import ch.ge.ve.protopoc.service.support.RandomGenerator;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static ch.ge.ve.protopoc.arithmetic.BigIntegerArithmetic.modExp;
import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;

/**
 * Algorithms related to the vote casting phase
 */
public class VoteCastingClientAlgorithms {
    private static final Logger log = LoggerFactory.getLogger(VoteCastingClientAlgorithms.class);
    private final PublicParameters publicParameters;
    private final Hash hash;
    private final RandomGenerator randomGenerator;
    private final GeneralAlgorithms generalAlgorithms;
    private final Conversion conversion = new Conversion();

    public VoteCastingClientAlgorithms(PublicParameters publicParameters, GeneralAlgorithms generalAlgorithms, RandomGenerator randomGenerator, Hash hash) {
        this.publicParameters = publicParameters;
        this.hash = hash;
        this.randomGenerator = randomGenerator;
        this.generalAlgorithms = generalAlgorithms;
    }

    /**
     * Algorithm 7.18: GenBallot
     *
     * @param upper_x the voting code
     * @param bold_s  voters selection (indices)
     * @param pk      the public encryption key
     * @return the combined ballot, OT query and random elements used
     * @throws IncompatibleParametersException when there is an issue with the public parameters
     */
    public BallotQueryAndRand genBallot(String upper_x, List<Integer> bold_s, EncryptionPublicKey pk) {
        Preconditions.checkArgument(bold_s.size() > 0,
                "There needs to be at least one selection");
        Preconditions.checkArgument(bold_s.stream().sorted().collect(Collectors.toList()).equals(bold_s),
                "The list of selections needs to be ordered");
        Preconditions.checkArgument(bold_s.stream().allMatch(i -> i >= 1),
                "Selections must be strictly positive");
        Preconditions.checkArgument(bold_s.stream().distinct().count() == bold_s.size(),
                "All selections must be distinct");
        Preconditions.checkArgument(generalAlgorithms.isMember(pk.getPublicKey()),
                "The key must be a member of G_q");
        Preconditions.checkArgument(BigInteger.ONE.compareTo(pk.getPublicKey()) != 0,
                "The key must not be 1");

        BigInteger p_circ = publicParameters.getIdentificationGroup().getP_circ();
        BigInteger g_circ = publicParameters.getIdentificationGroup().getG_circ();
        BigInteger p = publicParameters.getEncryptionGroup().getP();
        BigInteger q = publicParameters.getEncryptionGroup().getQ();
        BigInteger g = publicParameters.getEncryptionGroup().getG();

        BigInteger x = conversion.toInteger(upper_x, publicParameters.getA_x());
        BigInteger x_circ = modExp(g_circ, x, p_circ);

        List<BigInteger> bold_q = computeBoldQ(bold_s);
        BigInteger m = computeM(bold_q, p);
        ObliviousTransferQuery query = genQuery(bold_q, pk);
        BigInteger a = computeA(query, p);
        BigInteger r = computeR(query, q);
        BigInteger b = modExp(g, r, p);
        NonInteractiveZKP pi = genBallotProof(x, m, r, x_circ, a, b, pk);
        BallotAndQuery alpha = new BallotAndQuery(x_circ, query.getBold_a(), b, pi);

        return new BallotQueryAndRand(alpha, query.getBold_r());
    }

    private List<BigInteger> computeBoldQ(List<Integer> bold_s) {
        List<BigInteger> bold_q;
        try {
            bold_q = getSelectedPrimes(bold_s);
        } catch (NotEnoughPrimesInGroupException e) {
            throw new IncompatibleParametersException("Encryption Group too small for selection");
        }
        return bold_q;
    }

    private BigInteger computeM(List<BigInteger> bold_q, BigInteger p) {
        BigInteger m = bold_q.stream().reduce(BigInteger::multiply)
                .orElse(ONE);
        if (m.compareTo(p) >= 0) {
            throw new IncompatibleParametersException("(k,n) is incompatible with p");
        }
        return m;
    }

    private BigInteger computeA(ObliviousTransferQuery query, BigInteger p) {
        return query.getBold_a().stream().reduce(BigInteger::multiply)
                .orElse(ONE)
                .mod(p);
    }

    private BigInteger computeR(ObliviousTransferQuery query, BigInteger q) {
        return query.getBold_r().stream().reduce(BigInteger::add)
                .orElse(ZERO)
                .mod(q);
    }


    /**
     * Algorithm 7.19: getSelectedPrimes
     *
     * @param bold_s the indices of the selected primes (in increasing order, 1-based)
     * @return the list of the primes selected
     */
    public List<BigInteger> getSelectedPrimes(List<Integer> bold_s) throws NotEnoughPrimesInGroupException {
        Preconditions.checkArgument(bold_s.size() > 0,
                "There needs to be at least one selection");
        Preconditions.checkArgument(bold_s.stream().allMatch(i -> i >= 1),
                "Selections must be strictly positive");
        Preconditions.checkArgument(
                bold_s.stream().sorted().collect(Collectors.toList()).equals(bold_s),
                "The elements must be sorted");
        Preconditions.checkArgument(bold_s.stream().distinct().count() == bold_s.size(),
                "All selections must be distinct");
        Integer s_k = bold_s.get(bold_s.size() - 1);
        List<BigInteger> primes = generalAlgorithms.getPrimes(s_k);

        return bold_s.stream()
                .map(s_i -> s_i - 1) // s_i is 1-based
                .map(primes::get)
                .collect(Collectors.toList());
    }

    /**
     * Algorithm 7.20: GenQuery
     *
     * @param bold_q the selected primes
     * @param pk     the public encryption key
     * @return the generated oblivious transfer query
     */
    public ObliviousTransferQuery genQuery(List<BigInteger> bold_q, EncryptionPublicKey pk) {
        Preconditions.checkArgument(generalAlgorithms.isMember(pk.getPublicKey()),
                "The key must be a member of G_q");
        Preconditions.checkArgument(BigInteger.ONE.compareTo(pk.getPublicKey()) != 0,
                "The key must not be 1");
        BigInteger q = publicParameters.getEncryptionGroup().getQ();
        BigInteger p = publicParameters.getEncryptionGroup().getP();

        List<BigInteger> bold_a = new ArrayList<>();
        List<BigInteger> bold_r = new ArrayList<>();

        for (BigInteger q_i : bold_q) {
            BigInteger r_i = randomGenerator.randomInZq(q);
            BigInteger a_i = q_i.multiply(modExp(pk.getPublicKey(), r_i, p)).mod(p);
            bold_a.add(a_i);
            bold_r.add(r_i);
        }

        return new ObliviousTransferQuery(bold_a, bold_r);
    }

    /**
     * Algorithm 7.21: GenBallotProof
     *
     * @param x      first half of voting credentials
     * @param m      encoded selections, m \isin G_q
     * @param r      randomization
     * @param x_circ second half of voting credentials
     * @param a      first half of ElGamal encryption
     * @param b      second half of ElGamal encryption
     * @param pk     encryption key
     * @return a non interactive proof of knowledge for the ballot
     */
    public NonInteractiveZKP genBallotProof(
            BigInteger x,
            BigInteger m,
            BigInteger r,
            BigInteger x_circ,
            BigInteger a,
            BigInteger b,
            EncryptionPublicKey pk) {
        Preconditions.checkArgument(x.compareTo(publicParameters.getIdentificationGroup().getQ_circ()) < 1,
                "The private credential must be in Z_q_circ");
        Preconditions.checkArgument(generalAlgorithms.isMember_G_q_circ(x_circ), "x_circ must be in G_q_circ");
        Preconditions.checkArgument(generalAlgorithms.isMember(m), "m must be in G_q");
        Preconditions.checkArgument(r.compareTo(publicParameters.getEncryptionGroup().getQ()) < 1,
                "");
        Preconditions.checkArgument(generalAlgorithms.isMember(a), "a must be in G_q");
        Preconditions.checkArgument(generalAlgorithms.isMember(b), "b must be in G_q");
        Preconditions.checkArgument(generalAlgorithms.isMember(pk.getPublicKey()),
                "The key must be a member of G_q");
        IdentificationGroup identificationGroup = publicParameters.getIdentificationGroup();
        BigInteger p_circ = identificationGroup.getP_circ();
        BigInteger q_circ = identificationGroup.getQ_circ();
        BigInteger g_circ = identificationGroup.getG_circ();

        EncryptionGroup encryptionGroup = publicParameters.getEncryptionGroup();
        BigInteger p = encryptionGroup.getP();
        BigInteger q = encryptionGroup.getQ();
        BigInteger g = encryptionGroup.getG();

        log.debug(String.format("genBallotProof: a = %s", a));

        BigInteger omega_1 = randomGenerator.randomInZq(q_circ);
        BigInteger omega_2 = randomGenerator.randomInGq(encryptionGroup);
        BigInteger omega_3 = randomGenerator.randomInZq(q);

        BigInteger t_1 = modExp(g_circ, omega_1, p_circ);
        BigInteger t_2 = omega_2.multiply(modExp(pk.getPublicKey(), omega_3, p)).mod(p);
        BigInteger t_3 = modExp(g, omega_3, p);

        BigInteger[] y = new BigInteger[]{x_circ, a, b};
        BigInteger[] t = new BigInteger[]{t_1, t_2, t_3};
        BigInteger c = generalAlgorithms.getNIZKPChallenge(y, t, q.min(q_circ));
        log.debug(String.format("genBallotProof: c = %s", c));

        BigInteger s_1 = omega_1.add(c.multiply(x)).mod(q_circ);
        BigInteger s_2 = omega_2.multiply(modExp(m, c, p)).mod(p);
        BigInteger s_3 = omega_3.add(c.multiply(r)).mod(q);
        List<BigInteger> s = Arrays.asList(s_1, s_2, s_3);

        return new NonInteractiveZKP(Arrays.asList(t), s);
    }

    /**
     * Algorithm 7.22: GetPointMatrix
     *
     * @param bold_beta the vector of the oblivious transfer replies (from the different authorities)
     * @param bold_k    the vector of allowed number of selections per election
     * @param bold_s    the vector of selected primes
     * @param bold_r    the vector of randomizations used for the OT query
     * @return the point matrix corresponding to the replies of the s authorities for the k selections
     * @throws InvalidObliviousTransferResponseException when one of the points would be outside the defined space
     */
    public List<List<Point>> getPointMatrix(
            List<ObliviousTransferResponse> bold_beta,
            List<Integer> bold_k,
            List<Integer> bold_s,
            List<BigInteger> bold_r) throws InvalidObliviousTransferResponseException {
        List<List<Point>> bold_P = new ArrayList<>();

        for (ObliviousTransferResponse beta_j : bold_beta) {
            bold_P.add(getPoints(beta_j, bold_k, bold_s, bold_r));
        }

        return bold_P;
    }

    /**
     * Algorithm 7.23: GetPoints
     *
     * @param beta   the OT response (from one authority)
     * @param bold_k the vector of allowed number of selections per election
     * @param bold_s the vector of selected primes
     * @param bold_r the vector of randomizations used for the OT query
     * @return the points corresponding to the authority's reply for the k selections
     * @throws InvalidObliviousTransferResponseException when one of the points would be outside the defined space
     */
    public List<Point> getPoints(
            ObliviousTransferResponse beta,
            List<Integer> bold_k,
            List<Integer> bold_s,
            List<BigInteger> bold_r) throws InvalidObliviousTransferResponseException {
        List<Point> bold_p = new ArrayList<>();
        List<BigInteger> b = beta.getB();
        byte[][] c = beta.getC();
        List<BigInteger> d = beta.getD();
        BigInteger p = publicParameters.getEncryptionGroup().getP();
        BigInteger p_prime = publicParameters.getPrimeField().getP_prime();
        int upper_l_m = publicParameters.getL_m() / 8;

        int i = 0; // 0 based indices in java, as opposed to the 1-based specification
        for (int j = 0; j < bold_k.size(); j++) {
            for (int l = 0; l < bold_k.get(j); l++) {
                log.debug("c[" + (bold_s.get(i) - 1) + "] = " + Arrays.toString(c[bold_s.get(i) - 1]));
                BigInteger k = b.get(i).multiply(modExp(d.get(j), bold_r.get(i).negate(), p)).mod(p);
                byte[] bold_upper_k = computeBoldUpperK(upper_l_m, k);
                byte[] M_i = ByteArrayUtils.xor(
                        // selections are 1-based
                        c[bold_s.get(i) - 1],
                        bold_upper_k);
                BigInteger x_i = conversion.toInteger(ByteArrayUtils.extract(M_i, 0, upper_l_m / 2));
                BigInteger y_i = conversion.toInteger(ByteArrayUtils.extract(M_i, upper_l_m / 2, M_i.length));
                if (log.isDebugEnabled()) {
                    log.debug(String.format("Decoding %s as  point : %d <%s, %s>", Arrays.toString(M_i), i, x_i, y_i));
                }

                if (x_i.compareTo(p_prime) >= 0 || y_i.compareTo(p_prime) >= 0) {
                    throw new InvalidObliviousTransferResponseException("x_i >= p' or y_i >= p'");
                }
                bold_p.add(new Point(x_i, y_i));
                i++;
            }
        }

        return bold_p;
    }

    private byte[] computeBoldUpperK(int upper_l_m, BigInteger k) {
        byte[] bold_upper_k = new byte[0];
        int upperbound = (int) Math.ceil((double) upper_l_m / (publicParameters.getSecurityParameters().getL() / 8.0));
        for (int z = 1; z <= upperbound; z++) {
            bold_upper_k = ByteArrayUtils.concatenate(bold_upper_k, hash.recHash_L(k, BigInteger.valueOf(z)));
        }
        bold_upper_k = ByteArrayUtils.truncate(bold_upper_k, upper_l_m);
        return bold_upper_k;
    }

    /**
     * Algorithm 7.24: GetReturnCodes
     *
     * @param bold_P the point matrix containing the responses for each of the authorities
     * @return the return codes corresponding to the point matrix
     */
    public List<String> getReturnCodes(List<List<Point>> bold_P) {
        Preconditions.checkArgument(bold_P.size() == publicParameters.getS());
        int length = bold_P.get(0).size();
        Preconditions.checkArgument(bold_P.stream().allMatch(l -> l.size() == length));
        List<Character> A_r = publicParameters.getA_r();

        List<String> rc = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            byte[] rc_i = new byte[publicParameters.getL_r() / 8];
            for (int j = 0; j < publicParameters.getS(); j++) {
                rc_i = ByteArrayUtils.xor(rc_i, ByteArrayUtils.truncate(
                        hash.recHash_L(bold_P.get(j).get(i)),
                        publicParameters.getL_r() / 8));
            }
            rc.add(conversion.toString(rc_i, A_r));
        }
        return rc;
    }
}
